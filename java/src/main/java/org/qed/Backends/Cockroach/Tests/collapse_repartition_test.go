package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestCollapseRepartition(t *testing.T) {
	rule := loadRule(t, "CollapseRepartition")
	e := newEnv(t)
	s, _ := e.scan("t", 3)
	inner := e.f.ConstructProject(s, nil, s.Relational().OutputCols)
	before := e.f.ConstructProject(inner, nil, inner.Relational().OutputCols)
	want := e.f.ConstructProject(s, nil, s.Relational().OutputCols)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, s.Relational().OutputCols))
	narrow, nc := e.scan("t", 3)
	inner2 := e.f.ConstructProject(narrow, nil, narrow.Relational().OutputCols)
	e.expectNoMatch(rule, e.f.ConstructProject(inner2, nil, opt.MakeColSet(nc[0], nc[1])))
	sel := e.f.ConstructSelect(narrow, e.filters(e.f.ConstructEq(e.v(nc[0]), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructProject(sel, nil, sel.Relational().OutputCols))
}
