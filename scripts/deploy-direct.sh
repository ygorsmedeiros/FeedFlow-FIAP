#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

region=${AWS_REGION:-us-east-2}
main=feedflow-dev
monitoring=feedflow-dev-monitoring
case "${1:-}" in
  ''|--dry-run) ;;
  *) echo "Uso: $0 [--dry-run]" >&2; exit 2 ;;
esac
(( $# <= 1 )) || exit 2
dry=${1:-}
: "${ADMIN_EMAIL:?Defina ADMIN_EMAIL}"
export AWS_PAGER=
# Preserve argumentos AWS que contêm caminhos ao executar no Git Bash.
export MSYS2_ARG_CONV_EXCL='*'

run() {
  if [[ $dry == --dry-run ]]; then
    printf '[dry-run]'
    for arg in "$@"; do
      if [[ $arg == ParameterKey=AdminEmail,* ]]; then
        printf ' %s' 'ParameterKey=AdminEmail,ParameterValue=[oculto]'
      else
        printf ' %q' "$arg"
      fi
    done
    printf '\n'
  else
    "$@"
  fi
}

out() {
  local value
  value=$(aws cloudformation describe-stacks --stack-name "$main" --region "$region" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue | [0]" --output text)
  if [[ -z $value || $value == None ]]; then
    echo "Output obrigatório ausente em $main: $1" >&2
    return 1
  fi
  printf '%s\n' "$value"
}

api=$(out ApiFunctionName)
notifications=$(out NotificationsFunctionName)
reports=$(out ReportsFunctionName)
api_url=$(out ApiUrl)
if [[ ! $api_url =~ ^https://([a-z0-9]+)\.execute-api\.([a-z0-9-]+)\.amazonaws\.com(/.*)?$ || ${BASH_REMATCH[2]:-} != "$region" ]]; then
  echo "ApiUrl não identifica um API Gateway na região $region" >&2
  exit 1
fi
api_id=${BASH_REMATCH[1]}
notifications_queue=$(out NotificationsFailureQueueUrl)
reports_queue=$(out ReportsFailureQueueUrl)
api_log=$(out ApiLogGroup)
modules=(feedflow-api feedflow-notifications feedflow-reports)
functions=("$api" "$notifications" "$reports")
for module in "${modules[@]}"; do
  [[ -s $module/target/function.zip ]] || { echo "ZIP ausente: $module/target/function.zip" >&2; exit 1; }
  jar tf "$module/target/function.zip" >/dev/null
done

params=(
  "ParameterKey=ApiFunctionName,ParameterValue=$api"
  "ParameterKey=NotificationsFunctionName,ParameterValue=$notifications"
  "ParameterKey=ReportsFunctionName,ParameterValue=$reports"
  "ParameterKey=HttpApiId,ParameterValue=$api_id"
  "ParameterKey=NotificationsQueueName,ParameterValue=${notifications_queue##*/}"
  "ParameterKey=ReportsQueueName,ParameterValue=${reports_queue##*/}"
  "ParameterKey=ApiLogGroupName,ParameterValue=$api_log"
  "ParameterKey=AdminEmail,ParameterValue=$ADMIN_EMAIL"
)
if stack=$(aws cloudformation describe-stacks --stack-name "$monitoring" --region "$region" \
    --query 'Stacks[0].StackStatus' --output text 2>&1); then
  case "$stack" in
    CREATE_COMPLETE|UPDATE_COMPLETE|UPDATE_ROLLBACK_COMPLETE) operation=update ;;
    *) echo "Estado da stack de monitoramento: $stack" >&2; exit 1 ;;
  esac
elif [[ $stack == *ValidationError* && $stack == *"Stack with id $monitoring does not exist"* ]]; then
  operation=create
else
  printf '%s\n' "$stack" >&2
  exit 1
fi

for index in "${!modules[@]}"; do
  name=${functions[index]}
  run aws lambda update-function-code --region "$region" --function-name "$name" \
    --zip-file "fileb://${modules[index]}/target/function.zip" --query '{State:State,Update:LastUpdateStatus}'
  run aws lambda wait function-updated-v2 --region "$region" --function-name "$name"
done

stack_command=(aws cloudformation "$operation-stack" --stack-name "$monitoring" --region "$region"
  --template-body file://monitoring.yaml --parameters "${params[@]}")
if [[ $dry == --dry-run ]]; then
  run "${stack_command[@]}"
elif result=$("${stack_command[@]}" 2>&1); then
  printf '%s\n' "$result"
elif [[ $operation == update && $result == *ValidationError* && $result == *"No updates are to be performed"* ]]; then
  echo 'Stack de monitoramento já atualizada.'
  exit 0
else
  printf '%s\n' "$result" >&2
  exit 1
fi
run aws cloudformation wait "stack-$operation-complete" --stack-name "$monitoring" --region "$region"
