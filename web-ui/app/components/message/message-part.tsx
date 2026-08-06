import * as React from "react";
import { useTranslation } from "react-i18next";

import type { ReasoningPart, ToolPart, UIMessagePart } from "~/types";

import { ChainOfThought } from "./chain-of-thought";
import { AudioPart } from "./parts/audio-part";
import { DocumentPart } from "./parts/document-part";
import { ImagePart } from "./parts/image-part";
import { ReasoningPart as ReasoningFallbackPart } from "./parts/reasoning-part";
import { ReasoningStepPart } from "./parts/reasoning-step-part";
import { TextPart } from "./parts/text-part";
import { ToolPart as ToolStepPart } from "./parts/tool-part";
import { VideoPart } from "./parts/video-part";

type ThinkingStep =
  | {
      type: "reasoning";
      reasoning: ReasoningPart;
    }
  | {
      type: "tool";
      tool: ToolPart;
    };

type MessagePartBlock =
  | {
      type: "thinking";
      steps: ThinkingStep[];
    }
  | {
      type: "content";
      part: UIMessagePart;
      index: number;
    };

export function groupMessageParts(parts: UIMessagePart[]): MessagePartBlock[] {
  const result: MessagePartBlock[] = [];
  let currentThinkingSteps: ThinkingStep[] = [];

  const flushThinkingSteps = () => {
    if (currentThinkingSteps.length === 0) return;
    result.push({ type: "thinking", steps: currentThinkingSteps });
    currentThinkingSteps = [];
  };

  parts.forEach((part, index) => {
    if (part.type === "reasoning") {
      currentThinkingSteps.push({ type: "reasoning", reasoning: part });
      return;
    }

    if (part.type === "tool") {
      currentThinkingSteps.push({ type: "tool", tool: part });
      return;
    }

    flushThinkingSteps();
    result.push({ type: "content", part, index });
  });

  flushThinkingSteps();
  return result;
}

interface MessagePartsProps {
  parts: UIMessagePart[];
  loading?: boolean;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
  onClickCitation?: (id: string) => void;
}

function renderContentPart(
  part: UIMessagePart,
  t: (key: string, options?: Record<string, unknown>) => string,
  loading?: boolean,
  onClickCitation?: (id: string) => void,
  imageContext?: { urls: string[]; index: number },
) {
  switch (part.type) {
    case "text":
      return <TextPart text={part.text} isAnimating={loading} onClickCitation={onClickCitation} />;
    case "image":
      return (
        <ImagePart
          url={part.url}
          siblingImages={imageContext?.urls}
          siblingIndex={imageContext?.index}
        />
      );
    case "video":
      return <VideoPart url={part.url} />;
    case "audio":
      return <AudioPart url={part.url} />;
    case "document":
      return <DocumentPart url={part.url} fileName={part.fileName} mime={part.mime} />;
    case "reasoning":
      return (
        <ReasoningFallbackPart reasoning={part.reasoning} isFinished={part.finishedAt != null} />
      );
    case "tool":
      return (
        <div className="text-xs text-muted-foreground">{t("message_parts.tool_step_hint")}</div>
      );
  }
}

export const MessageParts = React.memo(({
  parts,
  loading = false,
  onToolApproval,
  onClickCitation,
}: MessagePartsProps) => {
  const { t } = useTranslation("message");
  const groupedParts = React.useMemo(() => groupMessageParts(parts), [parts]);

  // 同一条消息里所有图片 part 的 url 列表 + 每个 part.index 对应的图片下标，
  // 这样点开任意一张图都能在灯箱里左右滑动看到这条消息的其它图片。
  const imageUrls = React.useMemo(
    () => parts.filter((part): part is Extract<UIMessagePart, { type: "image" }> => part.type === "image").map((part) => part.url),
    [parts],
  );
  const imageIndexByPartIndex = React.useMemo(() => {
    const map = new Map<number, number>();
    let imageCount = 0;
    parts.forEach((part, index) => {
      if (part.type === "image") {
        map.set(index, imageCount);
        imageCount += 1;
      }
    });
    return map;
  }, [parts]);

  return (
    <>
      {groupedParts.map((block, blockIndex) => {
        if (block.type === "thinking") {
          if (block.steps.length === 0) return null;

          const isReasoningOnlyBlock = block.steps.every((step) => step.type === "reasoning");
          const hasLoadingReasoning = block.steps.some(
            (step) => step.type === "reasoning" && step.reasoning.finishedAt == null,
          );
          const enableAdaptiveWidth = isReasoningOnlyBlock && !hasLoadingReasoning;

          return (
            <ChainOfThought
              key={`thinking-${blockIndex}`}
              className="my-1"
              collapsedAdaptiveWidth={enableAdaptiveWidth}
              collapseLabel={t("message_parts.collapse_thinking")}
              showMoreLabel={(hiddenCount) =>
                t("message_parts.expand_thinking_steps", { count: hiddenCount })
              }
              steps={block.steps}
              renderStep={(step, stepIndex, { isFirst, isLast }) => {
                if (step.type === "reasoning") {
                  const stepKey = step.reasoning.createdAt ?? `${blockIndex}-${stepIndex}`;
                  return (
                    <ReasoningStepPart
                      key={stepKey}
                      reasoning={step.reasoning}
                      collapsedAdaptiveWidth={enableAdaptiveWidth}
                      isFirst={isFirst}
                      isLast={isLast}
                    />
                  );
                }

                const stepKey = step.tool.toolCallId || `${blockIndex}-${stepIndex}`;
                return (
                  <ToolStepPart
                    key={stepKey}
                    tool={step.tool}
                    loading={loading && step.tool.output.length === 0}
                    onToolApproval={onToolApproval}
                    isFirst={isFirst}
                    isLast={isLast}
                  />
                );
              }}
            />
          );
        }

        const imageIndex = imageIndexByPartIndex.get(block.index);
        const imageContext =
          block.part.type === "image" && imageIndex != null
            ? { urls: imageUrls, index: imageIndex }
            : undefined;

        return (
          <React.Fragment key={`content-${block.index}`}>
            {renderContentPart(block.part, t, loading, onClickCitation, imageContext)}
          </React.Fragment>
        );
      })}
    </>
  );
});

interface MessagePartProps {
  part: UIMessagePart;
  loading?: boolean;
  onToolApproval?: (toolCallId: string, approved: boolean, reason: string, answer?: string) => void | Promise<void>;
  onClickCitation?: (id: string) => void;
}

export function MessagePart({ part, loading, onToolApproval, onClickCitation }: MessagePartProps) {
  return (
    <MessageParts
      parts={[part]}
      loading={loading}
      onToolApproval={onToolApproval}
      onClickCitation={onClickCitation}
    />
  );
}
