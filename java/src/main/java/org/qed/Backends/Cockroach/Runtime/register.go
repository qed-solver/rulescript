package rulescript

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
	"github.com/cockroachdb/cockroach/pkg/util/envutil"
)

var rulesDir = envutil.EnvOrDefaultString("COCKROACH_RULESCRIPT_DIR", "")

func init() {
	if rulesDir == "" {
		return
	}
	rules, err := LoadDir(rulesDir)
	if err != nil {
		panic(fmt.Sprintf("rulescript: %v", err))
	}
	fmt.Fprintf(os.Stderr, "rulescript: loaded %d rules from %s\n", len(rules), rulesDir)
	norm.RegisterExternalRules(func() norm.ExternalRule {
		return &registered{rules: rules, active: make(map[*Rule]bool)}
	})
}

func LoadDir(dir string) ([]*Rule, error) {
	paths, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil {
		return nil, err
	}
	sort.Strings(paths)
	rules := make([]*Rule, 0, len(paths))
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, err
		}
		rule, err := Parse(data)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", path, err)
		}
		rules = append(rules, rule)
	}
	return rules, nil
}

type registered struct {
	rules  []*Rule
	active map[*Rule]bool
}

func (r *registered) Apply(f *norm.Factory, e memo.RelExpr) (memo.RelExpr, bool) {
	for _, rule := range r.rules {
		if r.active[rule] {
			continue
		}
		r.active[rule] = true
		out, ok := rule.Apply(f, e)
		delete(r.active, rule)
		if ok {
			return out, true
		}
	}
	return nil, false
}
