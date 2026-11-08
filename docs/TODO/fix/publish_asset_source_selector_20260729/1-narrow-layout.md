# Narrow Layout

## Previous Behavior

The selected segment reserved width for a checkmark and long labels could expand beyond the available width.

## Change

Remove the redundant selected-state icon, enforce zero minimum width outside the weighted segment, reserve two text lines, and ellipsize text inside each segment.

## Expected Result

The mode selector stays within the publish form width and maintains equal segment sizes.

[DONE]
