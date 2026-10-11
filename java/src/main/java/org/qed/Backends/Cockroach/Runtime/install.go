package rulescript

import (
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
)

type Installed struct {
	rules   []*Rule
	active  map[*Rule]bool
	Applied []string
}

func Install(f *norm.Factory, rules ...*Rule) *Installed {
	in := &Installed{rules: rules, active: make(map[*Rule]bool)}
	f.AddExternalRules(in)
	return in
}

func (in *Installed) Apply(f *norm.Factory, e memo.RelExpr) (memo.RelExpr, bool) {
	for _, r := range in.rules {
		if in.active[r] {
			continue
		}
		in.active[r] = true
		out, ok := r.Apply(f, e)
		delete(in.active, r)
		if ok {
			in.Applied = append(in.Applied, r.Name)
			return out, true
		}
	}
	return nil, false
}
