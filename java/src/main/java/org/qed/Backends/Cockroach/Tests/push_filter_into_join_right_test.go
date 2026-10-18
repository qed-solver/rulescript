package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushFilterIntoJoinRight(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushFilterIntoJoinRight")

	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	leftVar := e.v(leftCols[0])
	rightVar := e.v(rightCols[0])
	before := e.f.ConstructInnerJoin(left, right, e.filters(
		e.f.ConstructGt(rightVar, e.num(10)),
		e.f.ConstructEq(leftVar, rightVar),
	), &memo.JoinPrivate{})
	rightFilter := e.f.ConstructSelect(right, e.filters(e.f.ConstructGt(rightVar, e.num(10))))
	want := e.f.ConstructInnerJoin(left, rightFilter, e.filters(e.f.ConstructEq(leftVar, rightVar)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructLeftJoin(left, right, e.filters(
		e.f.ConstructGt(rightVar, e.num(10)),
		e.f.ConstructEq(leftVar, rightVar),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
