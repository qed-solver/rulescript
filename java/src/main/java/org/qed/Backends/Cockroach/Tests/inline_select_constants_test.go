package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestInlineSelectConstants(t *testing.T) {
	rule := loadRule(t, "InlineSelectConstants")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("nn", 1)
	eq := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	inner := e.f.ConstructInnerJoin(left, right, eq, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(inner, e.filters(e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton)))
	want := e.f.ConstructSelect(inner, e.filters(e.f.ConstructIsNot(e.v(rightCols[0]), memo.NullSingleton)))
	e.t.Logf("before construction: %s", e.format(before))
	e.expectRewrite(rule, before, want)

	leftJoin := e.f.ConstructJoin(opt.LeftJoinOp, left, right, eq, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructSelect(leftJoin, e.filters(e.f.ConstructIsNot(e.v(leftCols[0]), memo.NullSingleton)))
	e.t.Logf("near-miss construction: %s", e.format(nearMiss))
	e.expectNoMatch(rule, nearMiss)
}
