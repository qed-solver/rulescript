package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyCaseSingleFalsyBranch(t *testing.T) {
	rule := loadRule(t, "SimplifyCaseSingleFalsyBranch")
	e := newEnv(t)
	scan, cols := e.scan("t", 3)

	cond := e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton)
	falseVal := e.f.ConstructFalse()
	nullVal := e.f.ConstructNull(types.Bool)
	before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(cond, falseVal),
		e.f.ConstructAnd(e.f.ConstructNot(cond), nullVal),
	)))
	want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)

	scan2, cols2 := e.scan("t", 3)
	cond2 := e.f.ConstructIs(e.v(cols2[2]), memo.NullSingleton)
	before2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(cond2, e.f.ConstructFalse()),
		e.f.ConstructAnd(e.f.ConstructNot(cond2), e.f.ConstructNull(types.Bool)),
	)))
	want2 := e.f.ConstructSelect(scan2, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before2, want2)

	scan3, cols3 := e.scan("t", 3)
	cond3 := e.f.ConstructIs(e.v(cols3[1]), memo.NullSingleton)
	nearMiss := e.f.ConstructSelect(scan3, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(cond3, e.f.ConstructTrue()),
		e.f.ConstructAnd(e.f.ConstructNot(cond3), e.f.ConstructNull(types.Bool)),
	)))
	e.expectNoMatch(rule, nearMiss)
}
