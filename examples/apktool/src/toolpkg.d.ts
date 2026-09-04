declare const ToolPkg: {
  registerAppLifecycleHook(options: {
    id: string;
    event: string;
    function: () => unknown;
  }): unknown;
};
