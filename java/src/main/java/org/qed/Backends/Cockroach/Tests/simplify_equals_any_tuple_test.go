package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyEqualsAnyTuple(t *testing.T) {
	rule := loadRule(t, "SimplifyEqualsAnyTuple")
	e := newEnv(t)

	outer, outerCols := e.scan("t", 1)
	inner, innerCols := e.scan("u", 1)
	condition := e.filters(e.f.ConstructEq(e.v(outerCols[0]), e.v(innerCols[0])))
	before := e.f.ConstructSemiJoin(outer, inner, condition, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(outer, e.filters(e.f.ConstructAny(inner, e.v(outerCols[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})))
	e.expectRewrite(rule, before, want)

	otherOuter, otherOuterCols := e.scan("t", 1)
	otherInner, otherInnerCols := e.scan("u", 1)
	nearMiss := e.f.ConstructInnerJoin(otherOuter, otherInner,
		e.filters(e.f.ConstructEq(e.v(otherOuterCols[0]), e.v(otherInnerCols[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
