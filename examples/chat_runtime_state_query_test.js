"use strict";

/* METADATA
{
  "name": "chat_runtime_state_query_test",
  "display_name": {
    "zh": "聊天运行状态查询测试",
    "en": "Chat Runtime State Query Test"
  },
  "description": {
    "zh": "按 chatId 查询聊天运行状态，或查询当前聊天状态。仅用于测试，不写入文件。",
    "en": "Query runtime state by chatId or query the current chat. Test-only and read-only."
  },
  "enabledByDefault": false,
  "category": "Chat",
  "tools": [
    {
      "name": "query_runtime_state_by_chat_id",
      "description": {
        "zh": "按指定 chatId 查询该聊天当前的运行状态。",
        "en": "Query the current runtime state for a specified chatId."
      },
      "parameters": [
        {
          "name": "chat_id",
          "description": {
            "zh": "要查询的聊天 ID",
            "en": "Chat ID to query"
          },
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "query_current_runtime_state",
      "description": {
        "zh": "查询当前打开聊天的运行状态，不需要填写 chatId。",
        "en": "Query the runtime state of the currently open chat without a chatId."
      },
      "parameters": []
    },
    {
      "name": "query_global_runtime_state",
      "description": {
        "zh": "查询全局聊天运行状态和当前活动聊天 ID。",
        "en": "Query global chat runtime state and active chat IDs."
      },
      "parameters": []
    }
  ]
}
*/

var ChatRuntimeStateQueryTest = (function () {
  function errorText(error) {
    if (error && typeof error.message === "string") return error.message;
    return String(error);
  }

  function normalizeResult(result) {
    if (!result) {
      return {
        success: false,
        message: "查询没有返回结果",
        data: null
      };
    }

    return {
      success: !!result.success,
      message: result.message || (result.success ? "查询成功" : "查询失败"),
      data: result.data || null
    };
  }

  async function query_runtime_state_by_chat_id(params) {
    var chatId = params && params.chat_id ? String(params.chat_id).trim() : "";
    if (!chatId) {
      return {
        success: false,
        message: "chat_id 不能为空",
        data: null
      };
    }

    try {
      var result = await Tools.Chat.getCurrentChatRuntimeState(chatId);
      var normalized = normalizeResult(result);
      normalized.queriedChatId = chatId;
      return normalized;
    } catch (error) {
      return {
        success: false,
        message: "按 chatId 查询失败: " + errorText(error),
        queriedChatId: chatId,
        data: null
      };
    }
  }

  async function query_current_runtime_state() {
    try {
      return normalizeResult(await Tools.Chat.getCurrentChatRuntimeState());
    } catch (error) {
      return {
        success: false,
        message: "查询当前聊天失败: " + errorText(error),
        data: null
      };
    }
  }

  async function query_global_runtime_state() {
    try {
      return normalizeResult(await Tools.Chat.getGlobalChatRuntimeState());
    } catch (error) {
      return {
        success: false,
        message: "查询全局状态失败: " + errorText(error),
        data: null
      };
    }
  }

  return {
    query_runtime_state_by_chat_id: query_runtime_state_by_chat_id,
    query_current_runtime_state: query_current_runtime_state,
    query_global_runtime_state: query_global_runtime_state
  };
})();

exports.query_runtime_state_by_chat_id = ChatRuntimeStateQueryTest.query_runtime_state_by_chat_id;
exports.query_current_runtime_state = ChatRuntimeStateQueryTest.query_current_runtime_state;
exports.query_global_runtime_state = ChatRuntimeStateQueryTest.query_global_runtime_state;
