# 语音输入回归

全量保留原有语音顺序、取消、空响应、部分输出、缓存及重试分类测试，并增加服务商出站音频结构检查；下列项目交由 Android Tests 运行。本地不运行测试。

## 场景与预期

- 硅基流动直接音频：发送 `audio_url.url`，保留 WAV/MP3 的 MIME 类型、Base64 数据及附加文字
- OpenAI 与阿里云直接音频：分别保留原始 Base64 和 Data URL 的 `input_audio` 格式
- 音视频混合、音频开关关闭及非用户消息角色：保持视频结构、文本处理及角色限制；摘要角色按原能力开关携带音频
- 对话与音频理解拒绝 `input_audio`，STT 成功：不重复换钥发送同一无效请求，聊天只保留最终采用的回答
- 音频请求临时重试后成功：首段正文前发布该请求自己的重试通知
- 文字请求临时重试：照常发布文字请求通知；最终失败保留实际异常及音频尝试证据
- 已输出回答或工具调用后连接中断：不重放请求；取消不进入下一路径
- 手动重试与历史中的已识别语音：复用转写；未解析录音按实际模型开关处理
- 硅基 STT multipart 配置：沿用现有配置的文件上传、模型及语言参数，成功结果用于文字对话

## 自动回归完整结果表

| 用例 | 所属测试 | 结果 |
| --- | --- | --- |
| `cachedTranscriptDoesNotReadConfigurationOrRecognizeAgain` | `VoiceInputFallbackTest` | 待 CI |
| `configuredAudioModelWinsWithoutStt` | `VoiceInputFallbackTest` | 待 CI |
| `unconfiguredAudioModelUsesStt` | `VoiceInputFallbackTest` | 待 CI |
| `failedAudioModelUsesSttAndReportsItsError` | `VoiceInputFallbackTest` | 待 CI |
| `emptyAudioUnderstandingUsesStt` | `VoiceInputFallbackTest` | 待 CI |
| `unreadableAudioConfigurationUsesStt` | `VoiceInputFallbackTest` | 待 CI |
| `audioModelCancellationDoesNotStartStt` | `VoiceInputFallbackTest` | 待 CI |
| `configurationCancellationDoesNotStartStt` | `VoiceInputFallbackTest` | 待 CI |
| `sttCancellationIsPropagated` | `VoiceInputFallbackTest` | 待 CI |
| `bothFailuresKeepAudioErrorAsEvidence` | `VoiceInputFallbackTest` | 待 CI |
| `emptySttIsAnError` | `VoiceInputFallbackTest` | 待 CI |
| `directAudioSuccessNeverStartsTextPath` | `VoiceInputFallbackTest` | 待 CI |
| `directAudioFailureBeforeContentStartsTextPath` | `VoiceInputFallbackTest` | 待 CI |
| `emptyDirectAudioCompletionStartsTextPath` | `VoiceInputFallbackTest` | 待 CI |
| `partialAnswerOrToolContentIsNeverReplayed` | `VoiceInputFallbackTest` | 待 CI |
| `directAudioCancellationDoesNotStartTextPath` | `VoiceInputFallbackTest` | 待 CI |
| `emptyTextResponseFailsWithOriginalDirectAudioError` | `VoiceInputFallbackTest` | 待 CI |
| `textResponseCancellationIsNotConvertedToRequestFailure` | `VoiceInputFallbackTest` | 待 CI |
| `threePathsFollowUserSpecifiedOrder` | `VoiceInputFallbackTest` | 待 CI |
| `recoveredVoiceTextDoesNotPublishRejectedAudioRetryNotice` | `VoiceInputFallbackTest` | 待 CI |
| `acceptedNativeAnswerPublishesItsNoticesBeforeContent` | `VoiceInputFallbackTest` | 待 CI |
| `textRequestRetriesStillPublishTheirOwnNotice` | `VoiceInputFallbackTest` | 待 CI |
| `finalFailureKeepsBothAttemptsWithoutPublishingIntermediateNotice` | `VoiceInputFallbackTest` | 待 CI |
| `analysisKeepsSpokenWordsAndObservationsSeparate` | `VoiceAnalysisTest` | 待 CI |
| `backgroundDescriptionCannotReplaceMissingSpeech` | `VoiceAnalysisTest` | 待 CI |
| `missingTranscriptFieldIsRejected` | `VoiceAnalysisTest` | 待 CI |
| `nonStringAnalysisFieldsAreRejectedInsteadOfBecomingSpokenWords` | `VoiceAnalysisTest` | 待 CI |
| `emptyObservationsAreValidAndSttDoesNotInventThem` | `VoiceAnalysisTest` | 待 CI |
| `oldMessagesWithoutObservationTagsStillRoundTrip` | `VoiceAnalysisTest` | 待 CI |
| `historicalVoiceKeepsTextObservationsWithoutResendingRecording` | `VoiceAnalysisTest` | 待 CI |
| `observationsEscapeMarkupAndAdditionalTextStaysOutsideTranscript` | `VoiceAnalysisTest` | 待 CI |
| `resolvedAudioReusesTranscriptWhileUnresolvedAudioHonorsCapability` | `VoiceAnalysisTest` | 待 CI |
| `invalidParametersStopWithoutRotatingKeys` | `ApiRequestRetryPolicyTest` | 待 CI |
| `invalidKeysRemainKeyFailuresWhenGenericRequestTypeIsPresent` | `ApiRequestRetryPolicyTest` | 待 CI |
| `unavailableModelsCanUseAnotherCandidateKey` | `ApiRequestRetryPolicyTest` | 待 CI |
| `quotaAndBalanceFailuresRetainRetries` | `ApiRequestRetryPolicyTest` | 待 CI |
| `transientHttpFailuresKeepRetriesDespiteGenericRequestErrors` | `ApiRequestRetryPolicyTest` | 待 CI |
| `transportAndResponseFailuresKeepRetries` | `ApiRequestRetryPolicyTest` | 待 CI |
| `singleKeyTransientFailuresAllowExactlyFiveRetries` | `ApiRequestRetryPolicyTest` | 待 CI |
| `invalidKeysAndModelsStopAfterFirstFailureWithoutOtherCandidates` | `ApiRequestRetryPolicyTest` | 待 CI |
| `invalidKeysAndModelsRotateWithoutRepeatingTheFailedKey` | `ApiRequestRetryPolicyTest` | 待 CI |
| `typedProtocolAndCertificateFailuresStopWithoutChangingKeys` | `ApiRequestRetryPolicyTest` | 待 CI |
| `providerFieldsDetermineBusinessClassWithoutScanningDiagnosticText` | `ApiRequestRetryPolicyTest` | 待 CI |
| `structuredServerAndTransportFailuresIgnoreUnrelatedDiagnosticKeywords` | `ApiRequestRetryPolicyTest` | 待 CI |
| `unsupportedAudioContentStopsEvenWhenGatewayReports503` | `ApiRequestRetryPolicyTest` | 待 CI |
| `siliconFlowWavUsesAudioUrlAndKeepsText` | `ProviderAudioContentTest` | 待 CI |
| `siliconFlowMpegPreservesMimeTypeAndBase64` | `ProviderAudioContentTest` | 待 CI |
| `openAiStillUsesRawBase64InputAudio` | `ProviderAudioContentTest` | 待 CI |
| `aliyunStillUsesDataUrlInputAudio` | `ProviderAudioContentTest` | 待 CI |
| `siliconFlowVideoKeepsVideoUrlAlongsideAudio` | `ProviderAudioContentTest` | 待 CI |
| `siliconFlowDisabledAudioKeepsTextOnly` | `ProviderAudioContentTest` | 待 CI |
| `siliconFlowNonUserRolesDoNotAttachAudio` | `ProviderAudioContentTest` | 待 CI |
| `siliconFlowSummaryUsesAudioUrl` | `ProviderAudioContentTest` | 待 CI |

## 实机结果表

| 操作 | 预期 | 结果 |
| --- | --- | --- |
| 使用支持音频的硅基流动 Qwen3-Omni 配置发送录音 | 首次请求采用 `audio_url` 并可识别录音 | 待填写 |
| 直接使用硅基 STT 转写录音 | multipart 上传并返回识别文字 | 待填写 |
| 重复报告中的语音输入 | 识别成功后无已放弃的音频错误卡片 | 待填写 |
| 手动重试已识别语音 | 复用转写文字 | 待填写 |
| 取消录音理解或响应 | 停止后续处理 | 待填写 |
