/** 保留原生工具异常的数据，非零终端退出也必须带回完整输出。 */
function toolFailure(error, failMessage) {
    const objectError = typeof error === 'object' && error !== null;
    const detail = objectError && typeof error.message === 'string' ? error.message : String(error);
    const result = {
        success: false,
        message: `${failMessage}: ${detail}`,
        error_stack: objectError && typeof error.stack === 'string' ? error.stack : ''
    };
    if (objectError && 'data' in error) {
        result.data = error.data;
    }
    return result;
}
module.exports = { toolFailure };
