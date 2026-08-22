const DEFAULT_SESSION_NAME = 'github_tools_session';
const DEFAULT_TIMEOUT_MS = 120000;

let terminalSessionId: string | null = null;
let terminalSessionName: string | null = null;
let sessionCreationPromise: Promise<string> | null = null;

function normalizeSessionName(sessionName?: string): string {
    const normalized = String(sessionName || '').trim();
    return normalized || DEFAULT_SESSION_NAME;
}

function invalidateSession(sessionId?: string): void {
    if (!sessionId || terminalSessionId === sessionId) {
        terminalSessionId = null;
        terminalSessionName = null;
    }
}

async function closeSessionQuietly(sessionId: string | null): Promise<void> {
    if (!sessionId) return;
    try {
        await Tools.System.terminal.close(sessionId);
    } catch (_error) {
        // A dead session is already closed from the tool's perspective.
    } finally {
        invalidateSession(sessionId);
    }
}

function isSessionLifecycleError(error: any): boolean {
    const message = String(error && error.message ? error.message : error || '').toLowerCase();
    const mentionsSession = message.includes('session') || message.includes('会话') || message.includes('pty');
    const mentionsLifecycle =
        message.includes('not exist') ||
        message.includes('does not exist') ||
        message.includes('closed') ||
        message.includes('invalid') ||
        message.includes('unavailable') ||
        message.includes('不存在') ||
        message.includes('关闭') ||
        message.includes('失效') ||
        message.includes('不可用');
    return mentionsSession && mentionsLifecycle;
}

function isTimeoutError(error: any): boolean {
    const message = String(error && error.message ? error.message : error || '').toLowerCase();
    return message.includes('timed out') || message.includes('timeout') || message.includes('超时');
}

function normalizeTimeout(timeoutMs?: number): number {
    const value = timeoutMs === undefined || timeoutMs === null
        ? DEFAULT_TIMEOUT_MS
        : Number(timeoutMs);
    if (!Number.isFinite(value) || value <= 0) {
        throw new Error('timeout_ms must be a positive number');
    }
    return Math.floor(value);
}

export async function getTerminalSession(sessionName?: string): Promise<string> {
    const normalizedName = normalizeSessionName(sessionName);
    if (terminalSessionId && terminalSessionName === normalizedName) {
        return terminalSessionId;
    }

    if (sessionCreationPromise) {
        return sessionCreationPromise;
    }

    sessionCreationPromise = (async () => {
        if (terminalSessionId) {
            await closeSessionQuietly(terminalSessionId);
        }

        const session = await Tools.System.terminal.create(normalizedName);
        if (!session || !session.sessionId) {
            throw new Error(`Terminal session creation returned no sessionId for ${normalizedName}`);
        }

        terminalSessionId = String(session.sessionId);
        terminalSessionName = normalizedName;
        return terminalSessionId;
    })();

    try {
        return await sessionCreationPromise;
    } finally {
        sessionCreationPromise = null;
    }
}

export async function terminalExec(params: {
    command: string;
    session_name?: string;
    timeout_ms?: number;
    close?: boolean;
}): Promise<any> {
    const sessionName = normalizeSessionName(params.session_name);
    const timeoutMs = normalizeTimeout(params.timeout_ms);
    let sessionId = await getTerminalSession(sessionName);

    try {
        let result: any;
        try {
            result = await Tools.System.terminal.exec(sessionId, params.command, timeoutMs);
        } catch (error) {
            if (isTimeoutError(error)) {
                await closeSessionQuietly(sessionId);
                throw error;
            }
            if (!isSessionLifecycleError(error)) {
                throw error;
            }

            // The Kotlin layer may have reclaimed the PTY. Recreate once, then retry.
            invalidateSession(sessionId);
            sessionId = await getTerminalSession(sessionName);
            result = await Tools.System.terminal.exec(sessionId, params.command, timeoutMs);
        }

        if (result && result.timedOut === true) {
            await closeSessionQuietly(sessionId);
            throw new Error(`Terminal command timed out after ${timeoutMs}ms`);
        }

        return result;
    } finally {
        if (params.close) {
            await closeSessionQuietly(sessionId);
        }
    }
}
