package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestOptimizeOneRowRelationSubquery(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "OptimizeOneRowRelationSubquery")
	left, leftCols := e.scan("t", 1)
	rightCol := e.f.Metadata().AddColumn("one", types.Bool)
	row := e.f.ConstructTuple(memo.ScalarListExpr{memo.TrueSingleton}, types.MakeTuple([]*types.T{types.Bool}))
	right := e.f.ConstructValues(memo.ScalarListExpr{row}, &memo.ValuesPrivate{Cols: opt.ColList{rightCol}, ID: e.f.Metadata().NextUniqueID()})
	join := e.f.ConstructInnerJoin(left, right, nil, &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, nil, opt.MakeColSet(leftCols[0], rightCol)).(memo.RelExpr)
	computedCol := rightCol
	want := e.f.ConstructProject(left, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), computedCol)}, opt.MakeColSet(leftCols[0])).(memo.RelExpr)
	e.expectRewrite(rule, before, want)

	falseCond := e.f.ConstructFiltersItem(e.f.ConstructFalse())
	nearJoin := e.f.ConstructInnerJoin(left, right, memo.FiltersExpr{falseCond}, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructProject(nearJoin, nil, opt.MakeColSet(leftCols[0], rightCol)).(memo.RelExpr)
	e.expectNoMatch(rule, nearMiss)
}

