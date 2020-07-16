package backgroundcat

import (
	"errors"
	"strings"
)

type MistakeParser func(string, logSource) (Mistake, error)

type severity string

// These get shown in the title of embed fields
const (
	SEVERE    severity = "‼" // Probably the direct cause of a crash
	IMPORTANT severity = "❗" // Important, probably worth looking into
)

type logSource int

const ( // Poor man's enum
	Unknown logSource = iota
	MultiMC
	Vanilla
)

type Mistake struct {
	severity severity
	message  string
}

var errNotApplicable = errors.New("not applicable to the log")

func AggregateMistakes(logs string) []Mistake {
	var mistakes []Mistake
	source := getLogSource(logs)
	for _, parser := range parsers {
		mistake, err := parser(logs, source)
		if err != nil {
			continue
		}
		mistakes = append(mistakes, mistake)
	}
	return mistakes
}

func getLogSource(logs string) logSource {
	switch {
	case strings.HasPrefix(logs, "MultiMC version"):
		return MultiMC
	}
	return Unknown // Or maybe Vanilla?
}

var parsers = [...]MistakeParser{
	missingFabricAPI,
	oldIntelGPUWin10,
	mmcProgramFiles,
	mmc32Bit,
}

func missingFabricAPI(logs string, source logSource) (Mistake, error) {
	if strings.Contains(logs, "net.fabricmc.loader.discovery.ModResolutionException: Could not find required mod:") &&
		strings.Contains(logs, "requires {fabric @") {
		return Mistake{SEVERE,
				"You are missing Fabric API, which is required by a mod. " +
					"**[Download it here](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**"},
			nil
	}
	return notApplicable()
}

func oldIntelGPUWin10(logs string, source logSource) (Mistake, error) {
	if strings.Contains(logs, "org.lwjgl.LWJGLException: Pixel format not accelerated") &&
		strings.Contains(logs, "Operating System: Windows 10") {
		return Mistake{IMPORTANT, "You seem to be using an Intel GPU that is not supported on Windows 10." +
				"**You will need to install an older version of Java, [see here for help](https://github.com/MultiMC/MultiMC5/wiki/Unsupported-Intel-GPUs)**"},
			nil
	}
	return notApplicable()
}

func mmcProgramFiles(logs string, source logSource) (Mistake, error) {
	if source == MultiMC && strings.Contains(logs, "Minecraft folder is:\nC:/Program Files") {
		return Mistake{SEVERE,
				"Your MultiMC installation is in Program Files, where MultiMC doesn't have permission to write.\n" +
					"**Move it somewhere else, like your Desktop.**"},
			nil
	}
	return notApplicable()
}

func mmc32Bit(logs string, source logSource) (Mistake, error) {
	if source == MultiMC && strings.Contains(logs, "Your Java architecture is not matching your system architecture.") {
		return Mistake{SEVERE,
				"You're using 32-bit Java. " +
					"[See here for help installing the correct version.](https://github.com/MultiMC/MultiMC5/wiki/Using-the-right-Java)"},
			nil
	}
	return notApplicable()
}

func notApplicable() (Mistake, error) {
	return Mistake{"", ""}, errNotApplicable
}
