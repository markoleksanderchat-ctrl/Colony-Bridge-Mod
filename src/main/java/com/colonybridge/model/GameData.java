package com.colonybridge.model;

public record GameData(
        String minecraftVersion,
        String loader,
        String loaderVersion,
        String mineColoniesVersion
) {
}
