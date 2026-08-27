import { useEffect, useRef, useState } from 'react';
import type { WebThinkingQualityMapping } from '../../../../util/chatTypes';

export function ThinkingQualitySlider({
  label,
  mapping,
  onChange,
  value
}: {
  label: string;
  mapping: WebThinkingQualityMapping;
  onChange: (optionId: string) => void;
  value: string;
}) {
  const [isDragging, setIsDragging] = useState(false);
  const { options } = mapping;
  const selectedIndex = options.findIndex((option) => option.id === value);
  // Keep hook order stable while the parent synchronizes an option after a model change.
  const normalizedSelectedIndex = selectedIndex >= 0 ? selectedIndex : 0;
  const [draftIndex, setDraftIndex] = useState(normalizedSelectedIndex);
  const draftIndexRef = useRef(normalizedSelectedIndex);

  useEffect(() => {
    setDraftIndex(normalizedSelectedIndex);
    draftIndexRef.current = normalizedSelectedIndex;
  }, [normalizedSelectedIndex]);

  if (selectedIndex < 0 || !options.length) {
    return null;
  }

  const safeDraftIndex = Math.min(draftIndex, options.length - 1);
  const selectedOption = options[safeDraftIndex];
  if (!selectedOption) {
    return null;
  }
  const progressFraction = options.length > 1 ? safeDraftIndex / (options.length - 1) : 1;
  const activeTrackWidth = `calc(${progressFraction * 100}% + ${16 - progressFraction * 20}px)`;
  const interiorOptionMaxWidth = options.length > 1
    ? `calc(${100 / (options.length - 1)}% - ${20 / (options.length - 1) + 2}px)`
    : '100%';
  const edgeOptionMaxWidth = options.length > 1
    ? `calc(${50 / (options.length - 1)}% + ${8 - 10 / (options.length - 1)}px)`
    : '100%';

  function handleChange(nextValue: string) {
    const nextIndex = Number(nextValue);
    if (!Number.isInteger(nextIndex) || !options[nextIndex]) {
      return;
    }
    draftIndexRef.current = nextIndex;
    setDraftIndex(nextIndex);
  }

  function commitDraft() {
    const option = options[draftIndexRef.current];
    if (!option || option.id === value) {
      return;
    }
    onChange(option.id);
  }

  function stopDragging() {
    setIsDragging(false);
    commitDraft();
  }

  return (
    <div className={`thinking-quality-slider ${isDragging ? 'is-dragging' : ''}`}>
      <div className="thinking-quality-slider-header">
        <span className="thinking-quality-slider-label">{label}</span>
        <output className="thinking-quality-slider-current" aria-live="polite">
          {selectedOption.label}
        </output>
      </div>

      <div className="thinking-quality-slider-control">
        <div aria-hidden="true" className="thinking-quality-slider-track">
          <span
            className="thinking-quality-slider-track-active"
            style={{ width: activeTrackWidth }}
          />
          {options.map((option, index) => {
            const stopFraction = options.length > 1 ? index / (options.length - 1) : 0;
            return (
              <span
                aria-hidden="true"
                className={`thinking-quality-slider-stop ${index <= safeDraftIndex ? 'is-active' : ''}`}
                key={option.id}
                style={{ left: `calc(${stopFraction * 100}% + ${10 * (1 - stopFraction * 2)}px)` }}
              />
            );
          })}
        </div>
        <input
          aria-label={label}
          aria-valuetext={selectedOption.label}
          className="thinking-quality-slider-input"
          max={options.length - 1}
          min={0}
          onBlur={stopDragging}
          onChange={(event) => handleChange(event.currentTarget.value)}
          onKeyUp={(event) => {
            if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'Home' || event.key === 'End') {
              commitDraft();
            }
          }}
          onPointerCancel={stopDragging}
          onPointerDown={(event) => {
            setIsDragging(true);
            event.currentTarget.setPointerCapture(event.pointerId);
          }}
          onPointerUp={(event) => {
            stopDragging();
            if (event.currentTarget.hasPointerCapture(event.pointerId)) {
              event.currentTarget.releasePointerCapture(event.pointerId);
            }
          }}
          step={1}
          type="range"
          value={safeDraftIndex}
        />
      </div>

      <div className="thinking-quality-slider-options">
        {options.map((option, index) => {
          const optionFraction = options.length > 1 ? index / (options.length - 1) : 0;
          const isEdgeOption = index === 0 || index === options.length - 1;
          let optionTransform = 'translateX(-50%)';
          if (options.length > 1 && index === 0) {
            optionTransform = 'translateX(-10px)';
          } else if (options.length > 1 && index === options.length - 1) {
            optionTransform = 'translateX(calc(-100% + 10px))';
          }
          return (
            <span
              className={`thinking-quality-slider-option ${index === safeDraftIndex ? 'is-active' : ''}`}
              key={option.id}
              style={{
                left: `calc(${optionFraction * 100}% + ${10 * (1 - optionFraction * 2)}px)`,
                maxWidth: isEdgeOption ? edgeOptionMaxWidth : interiorOptionMaxWidth,
                transform: optionTransform
              }}
            >
              {option.label}
            </span>
          );
        })}
      </div>
    </div>
  );
}
