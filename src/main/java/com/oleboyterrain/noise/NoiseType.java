package com.oleboyterrain.noise;

public enum NoiseType {
    PERLIN,
    SIMPLEX,
    RIDGE,
    FLAT,
    CELLULAR;

    public static NoiseType fromString(String s) {
        return switch (s.toLowerCase()) {
            case "perlin"   -> PERLIN;
            case "simplex"  -> SIMPLEX;
            case "ridge"    -> RIDGE;
            case "flat"     -> FLAT;
            case "cellular" -> CELLULAR;
            default -> throw new IllegalArgumentException("Unknown noise type: " + s +
                    ". Valid: perlin, simplex, ridge, flat, cellular");
        };
    }
}