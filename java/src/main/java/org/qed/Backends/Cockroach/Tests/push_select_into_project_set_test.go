package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoProjectSet(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoProjectSet")
	e := newEnv(t)
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	join := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	leftFilter := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.num(1)))
	rightFilter := e.filters(e.f.ConstructEq(e.v(rightCols[0]), e.num(2)))
	before := e.f.ConstructSelect(join, append(append(memo.FiltersExpr{}, leftFilter...), rightFilter...))
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(e.f.ConstructSelect(left, leftFilter), right, on, &memo.JoinPrivate{}), rightFilter)
	e.expectRewrite(rule, before, want)

	badLeft, badLeftCols := e.scan("t", 1)
	badRight, badRightCols := e.scan("u", 1)
	badOn := e.filters(e.f.ConstructEq(e.v(badLeftCols[0]), e.v(badRightCols[0])))
	badJoin := e.f.ConstructInnerJoin(badLeft, badRight, badOn, &memo.JoinPrivate{})
	bad := e.f.ConstructSelect(badJoin, e.filters(e.f.ConstructEq(e.v(badLeftCols[0]), e.num(1)), e.f.ConstructEq(e.v(badLeftCols[0]), e.num(2))))
	e.expectNoMatch(rule, bad)
}

