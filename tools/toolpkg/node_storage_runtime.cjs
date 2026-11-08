"use strict";
const fs = require("node:fs");
function createNodeJavaBridge() {
  return {
    callStatic(className, methodName, ...args) {
      if (className !== "android.system.Os") throw new Error(`Unsupported Java class: ${className}`);
      switch (methodName) {
        case "open": return fs.openSync(args[0], args[1], args[2]);
        case "fsync": return fs.fsyncSync(args[0]);
        case "close": return fs.closeSync(args[0]);
        case "rename": return fs.renameSync(args[0], args[1]);
        default: throw new Error(`Unsupported Os method: ${methodName}`);
      }
    }
  };
}
function createNodeIpc() {
  const handlers = new Map();
  return {
    on(channel, handler) {
      handlers.set(channel, handler);
      return () => handlers.delete(channel);
    },
    off(channel) { return handlers.delete(channel); },
    async call(channel, payload, options) {
      if (options?.targetRuntime !== "main") throw new Error("Node IPC requires targetRuntime: main");
      const handler = handlers.get(channel);
      if (!handler) throw new Error(`Unregistered IPC channel: ${channel}`);
      return await handler(payload, { channel, currentRuntime: "main" });
    }
  };
}
module.exports = { createNodeJavaBridge, createNodeIpc };
