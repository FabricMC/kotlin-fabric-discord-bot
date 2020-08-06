package versioncheck

import (
	"encoding/json"
	"fmt"
	"strings"
)

var jiraVersionsCache []string

//Set the initial jira versions in the cache
func populateInitialJiraVersions() error {
	versions, err := getJiraVersions()

	if err != nil {
		return err
	}

	for _, version := range versions {
		jiraVersionsCache = append(jiraVersionsCache, version.Name)
	}

	fmt.Printf("Loaded %d initial jira versions\n", len(jiraVersionsCache))

	return nil
}

func jiraUpdateCheck(callback func(message string) error) error {
	currentVersions, err := getJiraVersions()

	if err != nil {
		return err
	}

	for _, version := range currentVersions {

		if !contains(jiraVersionsCache, version.Name) {
			jiraVersionsCache = append(jiraVersionsCache, version.Name)
			if !strings.Contains(version.Name, "Future Version") {
				err = callback(jiraAsString(version))
				if err != nil {
					return err
				}
			}
		}
	}
	return nil
}

func jiraAsString(version JiraVersion) string {
	return fmt.Sprintf("A new version (%s) has been added to the minecraft issue tracker!", version.Name)
}

func getJiraVersions() ([]JiraVersion, error) {
	jsonStr, err := getJson("https://bugs.mojang.com/rest/api/latest/project/MC/versions")

	if err != nil {
		return nil, err
	}

	var versions []JiraVersion
	err = json.Unmarshal([]byte(jsonStr), &versions)

	return versions, err
}

type JiraVersion struct {
	Self            string `json:"self"`
	ID              string `json:"id"`
	Description     string `json:"description,omitempty"`
	Name            string `json:"name"`
	Archived        bool   `json:"archived"`
	Released        bool   `json:"released"`
	ReleaseDate     string `json:"releaseDate,omitempty"`
	UserReleaseDate string `json:"userReleaseDate,omitempty"`
	ProjectID       int    `json:"projectId"`
	StartDate       string `json:"startDate,omitempty"`
	UserStartDate   string `json:"userStartDate,omitempty"`
}
