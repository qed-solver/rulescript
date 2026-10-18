package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMapEqualityIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "MapEqualityIntoJoinLeftAndRight")
	e := newEnv(t)

	left, leftCols := e.scan("nn3", 2)
	right, rightCols := e.scan("u", 1)
	ax, ay, bx := e.v(leftCols[0]), e.v(leftCols[1]), e.v(rightCols[0])
	before := e.f.ConstructInnerJoin(left, right, e.filters(
		e.f.ConstructEq(ax, bx),
		e.f.ConstructEq(bx, ay),
	), &memo.JoinPrivate{})

	want := e.f.ConstructInnerJoin(left, right, e.filters(
		e.f.ConstructEq(bx, ay),
		e.f.ConstructEq(ax, ay),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearLeft, nearLeftCols := e.scan("nn3", 2)
	nearRight, nearRightCols := e.scan("u", 1)
	nearMiss := e.f.ConstructInnerJoin(nearLeft, nearRight, e.filters(
		e.f.ConstructEq(e.v(nearLeftCols[0]), e.v(nearRightCols[0])),
		e.f.ConstructEq(e.v(nearLeftCols[1]), e.v(nearRightCols[0])),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
