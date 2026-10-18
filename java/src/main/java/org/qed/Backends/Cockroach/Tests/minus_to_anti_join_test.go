package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMinusToAntiJoin(t *testing.T) {
	rule := loadRule(t, "MinusToAntiJoin")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	input := e.f.ConstructExcept(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{lc[0]},
	})
	join := e.f.ConstructAntiJoin(left, right, e.filters(e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructGroupBy(join, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	e.expectRewrite(rule, input, want)

	nearLeft, nlc := e.scan("u", 1)
	nearRight, nrc := e.scan("t", 1)
	near := e.f.ConstructExceptAll(nearLeft, nearRight, &memo.SetPrivate{
		LeftCols: opt.ColList{nlc[0]}, RightCols: opt.ColList{nrc[0]}, OutCols: opt.ColList{nlc[0]},
	})
	e.expectNoMatch(rule, near)
}
