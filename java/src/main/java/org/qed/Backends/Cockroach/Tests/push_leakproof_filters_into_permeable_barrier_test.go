package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestPushLeakproofFiltersIntoPermeableBarrier(t *testing.T) {
	rule := loadRule(t, "PushLeakproofFiltersIntoPermeableBarrier")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	project := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	leakproof := e.f.ConstructGt(e.v(cols[0]), e.num(0))
	remaining := e.f.ConstructLt(e.v(cols[0]), e.num(10))
	before := e.f.ConstructSelect(project, e.filters(leakproof, remaining))
	filtered := e.f.ConstructSelect(s, e.filters(leakproof))
	want := e.f.ConstructSelect(e.f.ConstructProject(filtered, nil, opt.MakeColSet(cols[0])), e.filters(remaining))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(project, e.filters(leakproof)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(leakproof, remaining)))
}
