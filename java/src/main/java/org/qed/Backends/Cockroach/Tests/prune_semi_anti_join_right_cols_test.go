package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneSemiAntiJoinRightCols(t *testing.T) {
	rule := loadRule(t, "PruneSemiAntiJoinRightCols")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 2)
	cond := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
	before := e.f.ConstructAntiJoin(left, right, e.filters(cond), &memo.JoinPrivate{})
	projectedRight := e.f.ConstructProject(right, nil, opt.MakeColSet(rightCols[0]))
	wantCond := e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))
	want := e.f.ConstructAntiJoin(left, projectedRight, e.filters(wantCond), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearMissLeft, nearMissLeftCols := e.scan("u", 1)
	nearMissRight, nearMissRightCols := e.scan("t", 2)
	nearMissCond := e.f.ConstructEq(e.v(nearMissLeftCols[0]), e.v(nearMissRightCols[1]))
	nearMiss := e.f.ConstructAntiJoin(nearMissLeft, nearMissRight, e.filters(nearMissCond), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
