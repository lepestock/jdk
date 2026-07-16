# Container-Oriented Testing Task

## Goal
Add JITTester-oriented generation and evaluation paths that stress container/array optimization code paths in HotSpot C2, including vectorization, `arraycopy` handling, folding/canonicalization around container loops, and optional Vector API equivalents.

## Size Strategy (Primary Lever)
Optimization triggers are more stable when driven by byte-length boundaries, not arbitrary element counts.

### Curated size anchors (in bytes)
Use a strong bias toward:
- `0, 1, 2, 3, 4, 7, 8, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 255, 256, 257, 511, 512`

Then map to element counts per type (`byte/short/int/long/float/double`).

### Distribution
- 70% from curated anchors.
- 30% random in `[1..512]` bytes (or mapped elements).

### Practical focus region
- Most runs in `16..256` bytes/equivalent elements.
- Some runs in `257..512` to exercise tail/peel and extended unroll behavior.

## Lane/Boundary Bias
For 128-bit SIMD-preferred environments (common on Apple Silicon), emphasize element counts around:
- `byte: 16`
- `short: 8`
- `int/float: 4`
- `long/double: 2`

Bias generated lengths around:
- exact multiples,
- `+1`,
- `-1` (where valid),
- `k*VL + r` with `r = 0, 1, VL-1`.

## Kernel Families to Generate
### 1) Map kernels
- `a[i] = b[i] + c`
- `a[i] = b[i] * c + d`

### 2) Zip kernels
- `a[i] = b[i] + c[i]`
- `a[i] = b[i] ^ c[i]`

### 3) Reductions
- sum, min, max, xor reductions.

### 4) Stencil-lite
- `a[i] = b[i-1] + b[i] + b[i+1]` with safe bounds.

### 5) Copy/fill
- manual copy loops,
- `System.arraycopy`,
- `Arrays.fill`.

### 6) Compare/select
- `a[i] = (b[i] > t) ? x : y`.

### 7) Overlap behaviors
- same-array `arraycopy` with overlapping and non-overlapping regions.

### 8) Vector API mirrors
- generate Vector API versions of scalar kernels for differential/code-shape stress.

## Variant Matrix Per Kernel
For each kernel, generate at least:
- aligned access (`offset=0`),
- misaligned access (`offset=1`),
- tail-heavy length (`N = k*VL + VL-1`).

## Coding Constraints for JIT-Load Quality
### Keep inner loops canonical
- `for (int i = 0; i < n; i++)` style preferred.

### Keep hot loops optimization-friendly
Avoid inside inner loops:
- allocations,
- virtual/interface calls,
- exception paths,
- complex branching unless branch kernels are the test target.

### Prevent dead-code elimination
- accumulate checksums/hashes and publish to observable sinks.

## Vector API-Specific Guidance
- Use `SPECIES_PREFERRED.loopBound(n)` for main vector body.
- Include explicit tail handling (masked or scalar).
- Where available, also generate fixed-species variants for coverage of fallback/code-shape differences.
- Keep scalar reference implementation and compare outputs.

## Common Pitfalls to Avoid
- Overly complex control flow in hot loop bodies.
- Source/destination aliasing ambiguity in kernels where alias freedom is desired.
- Random-only size selection without boundary bias.

## Acceptance Targets
- Generated corpus regularly includes boundary lengths and tail-heavy forms.
- `arraycopy`/fill and map/zip/reduction kernels appear in each batch.
- Optional Vector API kernels compile/run alongside scalar baselines.
- Runtime results remain deterministic and checksummed (for easy differential validation).

