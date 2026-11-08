export type ToolFailure = {
    success: false;
    message: string;
    data?: unknown;
    error_stack: string;
};

/** 异常载荷与成功返回类型独立，按原样传递，不把未知数据断言成成功结果。 */
export function toolFailure(error: unknown, failMessage: string): ToolFailure;
