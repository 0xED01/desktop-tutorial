// Minimal Node typings used by tools/tests (avoids @types/node dependency).
declare const process: { argv: string[]; exit(code?: number): never; env: Record<string, string | undefined>; hrtime: { bigint(): bigint } };
declare module 'node:fs' { const fs: any; export default fs; }
declare module 'node:path' { const p: any; export default p; }
declare function structuredClone<T>(v: T): T;
