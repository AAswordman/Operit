import { toolFailure } from './toolFailure';
import type { ToolFailure } from './toolFailure';

export type ToolResponse<T> = {
    success: true;
    message: string;
    data: T;
} | ToolFailure;

export async function wrap<T, P>(
    func: (params: P) => Promise<T>,
    params: P,
    successMessage: string,
    failMessage: string
): Promise<void> {
    try {
        const data = await func(params);
        const result: ToolResponse<T> = { success: true, message: successMessage, data };
        complete(result);
    } catch (error: unknown) {
        console.error(`[github] ${failMessage}`, error);
        complete(toolFailure(error, failMessage));
    }
}
