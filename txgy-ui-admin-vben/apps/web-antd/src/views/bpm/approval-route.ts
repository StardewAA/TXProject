export function buildApprovalDetailLocation(
  routeName: string,
  processInstanceId: string,
  taskId?: string,
  activityId?: string,
) {
  return {
    name: routeName,
    query: {
      id: processInstanceId,
      ...(taskId && { taskId }),
      ...(activityId && { activityId }),
    },
  };
}
