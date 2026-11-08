"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.wrap = wrap;
const toolFailure_1 = require("./toolFailure");
async function wrap(func, params, successMessage, failMessage) {
    try {
        const data = await func(params);
        const result = { success: true, message: successMessage, data };
        complete(result);
    }
    catch (error) {
        console.error(`[github] ${failMessage}`, error);
        complete((0, toolFailure_1.toolFailure)(error, failMessage));
    }
}
