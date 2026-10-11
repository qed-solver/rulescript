package rulescript_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/rulescript"
	"github.com/cockroachdb/cockroach/pkg/testutils/datapathutils"
)

func TestGeneratedRules(t *testing.T) {
	dir := datapathutils.TestDataPath(t, "rules")
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		name := strings.TrimSuffix(entry.Name(), ".json")
		t.Run(name, func(t *testing.T) {
			data, err := os.ReadFile(filepath.Join(dir, entry.Name()))
			if err != nil {
				t.Fatal(err)
			}
			rule, err := rulescript.Parse(data)
			if err != nil {
				t.Fatal(err)
			}
			if rule.GrowsOwnPattern() {
				t.Skip("its output contains its own pattern, so the server skips it")
			}
		})
	}
}
