package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestOuterJoinToAntiJoin(t *testing.T) {
	rule := loadRule(t, "OuterJoinToAntiJoin")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("nn", 1)
	cond := memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))}
	join := e.f.ConstructJoin(opt.LeftJoinOp, left, right, cond, &memo.JoinPrivate{})
	filtered := e.f.ConstructSelect(join, memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructIs(e.v(rightCols[0]), memo.NullSingleton))})
	before := e.f.ConstructProject(filtered, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	want := e.f.ConstructJoin(opt.AntiJoinOp, left, right, cond, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	if _, err := e.cat.ExecuteDDL("CREATE TABLE nullable (a INT)"); err != nil {
		t.Fatal(err)
	}
	nullableRight, nullableCols := e.scan("nullable", 1)
	nullableJoin := e.f.ConstructJoin(opt.LeftJoinOp, left, nullableRight, memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructEq(e.v(leftCols[0]), e.v(nullableCols[0])))}, &memo.JoinPrivate{})
	nullableFiltered := e.f.ConstructSelect(nullableJoin, memo.FiltersExpr{e.f.ConstructFiltersItem(e.f.ConstructIs(e.v(nullableCols[0]), memo.NullSingleton))})
	nullableBefore := e.f.ConstructProject(nullableFiltered, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	e.expectNoMatch(rule, nullableBefore)
}
