package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReplaceExceptWithFilter(t *testing.T) {
	rule := loadRule(t, "ReplaceExceptWithFilter")
	e := newEnv(t)
	base, cols := e.scan("t", 1)
	rightBase, rightCols := e.scan("t", 1)
	outCol := e.f.Metadata().AddColumn("except_out", e.f.Metadata().ColumnMeta(cols[0]).Type)
	leftPred := e.f.ConstructEq(e.v(cols[0]), e.num(1))
	rightPred := e.f.ConstructEq(e.v(rightCols[0]), e.num(2))
	left := e.f.ConstructSelect(base, e.filters(leftPred))
	right := e.f.ConstructSelect(rightBase, e.filters(rightPred))
	before := e.f.ConstructExcept(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{cols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{outCol},
	})
	leftRightPred := e.f.ConstructEq(e.v(cols[0]), e.num(2))
	notRight := e.f.ConstructNot(leftRightPred)
	rightNull := e.f.ConstructIs(leftRightPred, memo.NullSingleton)
	filterPred := e.f.ConstructOr(notRight, rightNull)
	filtered := e.f.ConstructSelect(left, e.filters(filterPred))
	rightLeftPred := e.f.ConstructEq(e.v(rightCols[0]), e.num(1))
	rightNotPred := e.f.ConstructNot(rightPred)
	rightNullPred := e.f.ConstructIs(rightPred, memo.NullSingleton)
	rightFilterPred := e.f.ConstructOr(rightNotPred, rightNullPred)
	rightFiltered := e.f.ConstructSelect(e.f.ConstructSelect(rightBase, e.filters(rightLeftPred)), e.filters(rightFilterPred))
	want := e.f.ConstructIntersect(filtered, rightFiltered, &memo.SetPrivate{
		LeftCols: opt.ColList{cols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{outCol},
	})
	e.expectRewrite(rule, before, want)

	e2 := newEnv(t)
	base, cols = e2.scan("t", 1)
	rightBase, rightCols = e2.scan("t", 1)
	leftPred = e2.f.ConstructEq(e2.v(cols[0]), e2.num(1))
	rightPred = e2.f.ConstructEq(e2.v(rightCols[0]), e2.num(2))
	left = e2.f.ConstructSelect(base, e2.filters(leftPred))
	right = e2.f.ConstructSelect(rightBase, e2.filters(rightPred))
	e2.expectNoMatch(rule, e2.f.ConstructExceptAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{cols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{cols[0]},
	}))
}
