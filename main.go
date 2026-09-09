package main

import (
	"bufio"
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
	"gopkg.in/yaml.v3"
)

type ModuleConfig struct {
	Name        string   `yaml:"name"`
	Description string   `yaml:"description"`
	Commands    []string `yaml:"commands"`
}

type TermuxDialogResult struct {
	Code int    `json:"code"`
	Text string `json:"text"`
}

func init() {
	consoleWriter := zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC3339}
	log.Logger = log.Output(consoleWriter).Level(zerolog.InfoLevel)
}

func main() {
	reader := bufio.NewReader(os.Stdin)

	for {
		renderMenu()

		input, err := reader.ReadString('\n')
		if err != nil {
			log.Error().Err(err).Msg("Gagal membaca input I/O")
			continue
		}

		choice := strings.TrimSpace(input)
		handleRouting(choice, reader)
	}
}

func renderMenu() {
	os.Stdout.WriteString("\n=========================================\n")
	os.Stdout.WriteString("  ADB MODULAR YAML NETWORK OPTIMIZER v4  \n")
	os.Stdout.WriteString("=========================================\n")
	os.Stdout.WriteString("1. ADB Pair (127.0.0.1:port)\n")
	os.Stdout.WriteString("2. ADB Connect (127.0.0.1:port)\n")
	os.Stdout.WriteString("3. Eksekusi Seluruh Modul YAML (.yaml)\n")
	os.Stdout.WriteString("4. Cek Status Daemon & Perangkat\n")
	os.Stdout.WriteString("0. Keluar (Tear-down)\n")
	os.Stdout.WriteString("Pilih menu [0-4]: ")
}

func handleRouting(choice string, reader *bufio.Reader) {
	switch choice {
	case "1":
		port := promptInput("ADB Pairing", "Masukkan Port Pairing (127.0.0.1):", reader)
		if port == "" {
			log.Warn().Msg("Proses Pairing dibatalkan.")
			return
		}

		code := promptInput("ADB Pairing", "Masukkan Pairing Code (6 Digit):", reader)
		if code == "" {
			log.Warn().Msg("Proses Pairing dibatalkan.")
			return
		}

		target := "127.0.0.1:" + port
		runADB("pair", target, code)

	case "2":
		port := promptInput("ADB Connect", "Masukkan Port Connect (127.0.0.1):", reader)
		if port == "" {
			log.Warn().Msg("Proses Connect dibatalkan.")
			return
		}

		target := "127.0.0.1:" + port
		runADB("connect", target)

	case "3":
		loadAndExecuteYAMLModules()

	case "4":
		runADB("devices")

	case "0":
		log.Info().Msg("Mematikan servis. Menutup koneksi...")
		os.Exit(0)

	default:
		log.Warn().Msg("Input I/O tidak valid.")
	}
}

func loadAndExecuteYAMLModules() {
	files, err := filepath.Glob("modules/*.yaml")
	if err != nil || len(files) == 0 {
		log.Warn().Msg("Tidak ada file modul .yaml ditemukan di direktori ./modules/")
		return
	}

	for _, file := range files {
		data, err := os.ReadFile(file)
		if err != nil {
			log.Error().Err(err).Msgf("Gagal membaca file modul: %s", file)
			continue
		}

		var mod ModuleConfig
		if err := yaml.Unmarshal(data, &mod); err != nil {
			log.Error().Err(err).Msgf("Format YAML tidak valid di file: %s", file)
			continue
		}

		log.Info().Msgf("Mengeksekusi Modul: [%s] - %s", mod.Name, mod.Description)

		for _, rawCmd := range mod.Commands {
			args := strings.Fields(rawCmd)
			if len(args) == 0 {
				continue
			}
			fullArgs := append([]string{"shell"}, args...)
			runADB(fullArgs...)
		}
	}

	sendAndroidNotification("ADB Optimizer", "Seluruh modul YAML berhasil diinjeksi!")
	log.Info().Msg("Eksekusi seluruh modul YAML selesai 100%.")
}

func promptInput(title string, label string, reader *bufio.Reader) string {
	cmd := exec.Command("termux-dialog", "text", "-t", title, "-i", label)
	out, err := cmd.Output()

	if err == nil && len(out) > 0 {
		var res TermuxDialogResult
		if err := json.Unmarshal(out, &res); err == nil {
			if res.Code == -1 && strings.TrimSpace(res.Text) != "" {
				return strings.TrimSpace(res.Text)
			}
			if res.Code == 0 {
				return ""
			}
		}
	}

	os.Stdout.WriteString(label + " ")
	input, _ := reader.ReadString('\n')
	return strings.TrimSpace(input)
}

func sendAndroidNotification(title string, content string) {
	cmd := exec.Command("termux-notification", "--title", title, "--content", content)
	_ = cmd.Run()
}

func runADB(args ...string) {
	cmd := exec.Command("adb", args...)
	output, err := cmd.CombinedOutput()

	cmdString := "adb " + strings.Join(args, " ")

	if err != nil {
		log.Error().Err(err).Str("output", strings.TrimSpace(string(output))).Msgf("Gagal eksekusi: %s", cmdString)
		return
	}

	log.Info().Msgf("Sukses: %s | %s", cmdString, strings.TrimSpace(string(output)))
}
