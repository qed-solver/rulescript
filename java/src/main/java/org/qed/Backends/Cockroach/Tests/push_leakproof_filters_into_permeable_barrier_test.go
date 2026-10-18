package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestPushLeakproofFiltersIntoPermeableBarrier(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushLeakproofFiltersIntoPermeableBarrier")
	input, cols := e.scan("t", 1)
	barrier := e.f.ConstructProject(input, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	leak := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	remaining := e.f.ConstructEq(e.v(cols[0]), e.num(2))
	before := e.f.ConstructSelect(barrier, e.filters(leak, remaining))

	filteredInput := e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(1))))
	wantBarrier := e.f.ConstructProject(filteredInput, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	want := e.f.ConstructSelect(wantBarrier, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(2))))
	e.expectRewrite(rule, before, want)

	nearMissInput, nearMissCols := e.scan("t", 1)
	nearMissBarrier := e.f.ConstructProject(nearMissInput, memo.ProjectionsExpr{}, opt.MakeColSet(nearMissCols[0]))
	nearMissLeak := e.f.ConstructEq(e.v(nearMissCols[0]), e.num(1))
	nearMissRemaining := e.f.ConstructEq(e.v(nearMissCols[0]), e.num(2))
	nearMiss := e.f.ConstructSelect(nearMissBarrier, e.filters(e.f.ConstructOr(nearMissLeak, nearMissRemaining)))
	e.expectNoMatch(rule, nearMiss)
}
