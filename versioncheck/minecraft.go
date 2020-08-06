package versioncheck

import (
	"encoding/json"
	"fmt"
	"time"
)

var minecraftVersionsCache []string

//Set the initial minecraft versions in the cache
func populateInitialMinecraftVersions() error {
	versions, err := getMinecraftVersions()

	if err != nil {
		return err
	}

	for _, version := range versions {
		minecraftVersionsCache = append(minecraftVersionsCache, version.ID)
	}

	fmt.Printf("Loaded %d initial minecraft versions\n", len(minecraftVersionsCache))

	return nil
}

func minecraftUpdateCheck(callback func(message string) error) error {
	currentVersions, err := getMinecraftVersions()

	if err != nil {
		return err
	}

	for _, version := range currentVersions {
		if !contains(minecraftVersionsCache, version.ID) {
			minecraftVersionsCache = append(minecraftVersionsCache, version.ID)

			err = callback(minecraftVersionAsString(version))

			if err != nil {
				return err
			}
		}
	}

	return nil
}

func minecraftVersionAsString(version Version) string {
	return fmt.Sprintf("A new %s version of minecraft was just released! : %s", version.Type, version.ID)
}

func getMinecraftVersions() ([]Version, error) {
	jsonStr, err := getJson("https://launchermeta.mojang.com/mc/game/version_manifest.json")

	if err != nil {
		return nil, err
	}

	var meta MinecraftMeta
	err = json.Unmarshal([]byte(jsonStr), &meta)
	return meta.Versions, err
}

type MinecraftMeta struct {
	Versions []Version `json:"versions"`
}

type Version struct {
	ID          string    `json:"id"`
	Type        string    `json:"type"`
	URL         string    `json:"url"`
	Time        time.Time `json:"time"`
	ReleaseTime time.Time `json:"releaseTime"`
}
