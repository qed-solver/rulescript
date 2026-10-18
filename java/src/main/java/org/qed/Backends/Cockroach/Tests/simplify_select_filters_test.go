package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifySelectFilters(t *testing.T) {
	rule := loadRule(t, "SimplifySelectFilters")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	p := e.f.ConstructEq(e.v(cols[0]), e.num(7))
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(p, e.f.ConstructNull(types.Bool))))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[0]), e.num(7))))
	e.expectRewrite(rule, before, want)

	nearMissScan, nearMissCols := e.scan("t", 2)
	nearMissP := e.f.ConstructEq(e.v(nearMissCols[0]), e.num(7))
	nearMiss := e.f.ConstructSelect(nearMissScan, e.filters(e.f.ConstructOr(nearMissP, memo.TrueSingleton)))
	e.expectNoMatch(rule, nearMiss)
}
