package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectCondLeftIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "PushSelectCondLeftIntoJoinLeftAndRight")
	e := newEnv(t)
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	cond := e.f.ConstructIs(e.v(leftCols[0]), memo.NullSingleton)
	before := e.f.ConstructSelect(e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{}), e.filters(cond))
	want := e.f.ConstructLeftJoin(
		e.f.ConstructSelect(left, e.filters(e.f.ConstructIs(e.v(leftCols[0]), memo.NullSingleton))),
		e.f.ConstructSelect(right, e.filters(e.f.ConstructIs(e.v(rightCols[0]), memo.NullSingleton))),
		on,
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	nearLeft, nearLeftCols := e.scan("t", 1)
	nearRight, nearRightCols := e.scan("t", 1)
	nearOn := e.filters(e.f.ConstructGt(e.v(nearLeftCols[0]), e.v(nearRightCols[0])))
	near := e.f.ConstructSelect(e.f.ConstructLeftJoin(nearLeft, nearRight, nearOn, &memo.JoinPrivate{}), e.filters(e.f.ConstructIs(e.v(nearLeftCols[0]), memo.NullSingleton)))
	e.expectNoMatch(rule, near)
}
