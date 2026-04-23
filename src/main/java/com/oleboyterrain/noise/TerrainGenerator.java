package com.oleboyterrain.noise;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BaseBlock;

public class TerrainGenerator {

    // Simple seeded noise helpers
    private final long seed;

    public TerrainGenerator() {
        this.seed = System.currentTimeMillis();
    }

    // ── Noise implementations ──────────────────────────────────────────────

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double a, double b, double t) { return a + t * (b - a); }

    private double grad(int hash, double x, double z) {
        int h = hash & 7;
        double u = h < 4 ? x : z;
        double v = h < 4 ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private int p(int x) {
        x = (x ^ (int)(seed >> 16)) & 0xFF;
        // Simple hash
        x = ((x >> 4) ^ x) * 0x45d9f3b;
        x = ((x >> 4) ^ x) * 0x45d9f3b;
        x = (x >> 4) ^ x;
        return x & 0xFF;
    }

    public double perlin(double x, double z) {
        int xi = (int)Math.floor(x) & 255;
        int zi = (int)Math.floor(z) & 255;
        double xf = x - Math.floor(x);
        double zf = z - Math.floor(z);
        double u = fade(xf), v = fade(zf);

        int aa = p(xi + p(zi));
        int ab = p(xi + p(zi + 1));
        int ba = p(xi + 1 + p(zi));
        int bb = p(xi + 1 + p(zi + 1));

        return lerp(
                lerp(grad(aa, xf, zf),       grad(ba, xf - 1, zf),     u),
                lerp(grad(ab, xf, zf - 1),   grad(bb, xf - 1, zf - 1), u),
                v
        );
    }

    public double simplex(double x, double z) {
        // Approximate simplex using skewed perlin
        double s = (x + z) * 0.366025;
        double xs = x + s, zs = z + s;
        return perlin(xs * 0.9, zs * 0.9) * 1.1;
    }

    public double ridge(double x, double z) {
        double n = perlin(x, z);
        return 1.0 - Math.abs(n) * 2.0;
    }

    public double cellular(double x, double z) {
        int xi = (int)Math.floor(x);
        int zi = (int)Math.floor(z);
        double minDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double cx = xi + dx + (p(xi + dx + p(zi + dz)) / 255.0);
                double cz = zi + dz + (p(xi + dx + p(zi + dz) + 7) / 255.0);
                double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (dist < minDist) minDist = dist;
            }
        }
        return Math.min(minDist, 1.0) * 2.0 - 1.0;
    }

    // Fractal Brownian Motion - stacks multiple octaves for natural look
    public double fbm(NoiseType type, double x, double z, int octaves, double lacunarity, double gain) {
        double value = 0, amplitude = 0.5, frequency = 1;
        for (int i = 0; i < octaves; i++) {
            double nx = x * frequency, nz = z * frequency;
            value += amplitude * getRaw(type, nx, nz);
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return value;
    }

    private double getRaw(NoiseType type, double x, double z) {
        return switch (type) {
            case PERLIN   -> perlin(x, z);
            case SIMPLEX  -> simplex(x, z);
            case RIDGE    -> ridge(x, z);
            case CELLULAR -> cellular(x, z);
            case FLAT     -> 0.0;
        };
    }

    // ── Main generation ───────────────────────────────────────────────────

    /**
     * @param session    WorldEdit edit session
     * @param region     The pos1/pos2 cuboid region
     * @param noiseType  Which noise algorithm
     * @param height     Max height variation in blocks
     * @param scale      Noise scale (higher = smoother/wider features)
     * @param octaves    How many noise layers (1-8, more = more detail)
     * @param roughness  How much each octave contributes (0.0-1.0)
     */
    public void generate(
            EditSession session,
            CuboidRegion region,
            NoiseType noiseType,
            int height,
            double scale,
            int octaves,
            double roughness,
            BaseBlock fillBlock
    ) throws MaxChangedBlocksException {

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        int baseY = min.y();
        int regionHeight = max.y() - min.y();

        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {

                double nx = x / scale;
                double nz = z / scale;

                double noiseVal;
                if (noiseType == NoiseType.FLAT) {
                    noiseVal = 0.5;
                } else {
                    noiseVal = fbm(noiseType, nx, nz, octaves, 2.0, roughness);
                    // Normalize from roughly [-1,1] to [0,1]
                    noiseVal = (noiseVal + 1.0) / 2.0;
                    noiseVal = Math.max(0, Math.min(1, noiseVal));
                }

                int surfaceY = baseY + (int)(noiseVal * height);
                surfaceY = Math.max(baseY, Math.min(baseY + regionHeight, surfaceY));

                // Fill the generated terrain mass with the chosen block.
                for (int y = baseY; y <= surfaceY; y++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    session.setBlock(pos, fillBlock);
                }

                // Clear air above surface up to max
                for (int y = surfaceY + 1; y <= max.y(); y++) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    session.setBlock(pos, com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                }
            }
        }
    }
}
