package rulescript

import (
	"fmt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
	"github.com/cockroachdb/cockroach/pkg/util/envutil"
	"os"
	"path/filepath"
	"sort"
)

var rulesDir = envutil.EnvOrDefaultString("COCKROACH_RULESCRIPT_DIR", "")

func init() {
	if rulesDir == "" { return }
	rules, skipped, err := LoadDir(rulesDir)
	if err != nil { panic(fmt.Sprintf("rulescript: %v", err)) }
	for _, problem := range skipped { fmt.Fprintf(os.Stderr, "rulescript: skipped %v\n", problem) }
	fmt.Fprintf(os.Stderr, "rulescript: loaded %d rules from %s\n", len(rules), rulesDir)
	norm.RegisterExternalRules(func() norm.ExternalRule {
		return &registered{rules: rules, active: make(map[*Rule]bool), guard: newGuard()}
	})
}

func LoadDir(dir string) ([]*Rule, []error, error) {
	paths, err := filepath.Glob(filepath.Join(dir, "*.json"))
	if err != nil { return nil, nil, err }
	sort.Strings(paths)
	rules := make([]*Rule, 0, len(paths))
	var skipped []error
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if err != nil { return nil, nil, err }
		rule, err := Parse(data)
		if err != nil {
			skipped = append(skipped, fmt.Errorf("%s: %w", filepath.Base(path), err))
			continue
		}
		rules = append(rules, rule)
	}
	return rules, skipped, nil
}

const maxApplications = 1000

type registered struct {
	rules   []*Rule
	active  map[*Rule]bool
	guard   *guard
	applied int
}

func (r *registered) Apply(f *norm.Factory, e memo.RelExpr) (memo.RelExpr, bool) {
	if r.applied >= maxApplications { return nil, false }
	for _, rule := range r.rules {
		if r.active[rule] || r.guard.skip(rule, e) { continue }
		r.active[rule] = true
		out, ok := rule.Apply(f, e)
		delete(r.active, rule)
		if ok {
			r.guard.record(rule, e, out)
			r.applied++
			return out, true
		}
	}
	return nil, false
}
