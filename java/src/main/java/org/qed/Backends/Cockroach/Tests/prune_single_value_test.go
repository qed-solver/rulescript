package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneSingleValue(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneSingleValue")
	left, cols := e.scan("t", 1)
	rightCol := e.f.Metadata().AddColumn("single", types.Bool)
	trueValue := e.f.ConstructTrue()
	right := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(memo.ScalarListExpr{trueValue}, types.MakeTuple([]*types.T{types.Bool})),
	}, &memo.ValuesPrivate{Cols: opt.ColList{rightCol}, ID: e.f.Metadata().NextUniqueID()})
	predicate := e.f.ConstructEq(e.v(rightCol), trueValue)
	before := e.f.ConstructInnerJoin(left, right, e.filters(predicate), &memo.JoinPrivate{})
	filtered := e.f.ConstructSelect(left, e.filters(e.f.ConstructEq(trueValue, trueValue)))
	constantCol := e.f.Metadata().AddColumn("constant", types.Bool)
	want := e.f.ConstructProject(filtered,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructTrue(), constantCol)},
		opt.MakeColSet(cols[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	secondRightCol := e.f.Metadata().AddColumn("second", types.Bool)
	twoRows := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(memo.ScalarListExpr{trueValue}, types.MakeTuple([]*types.T{types.Bool})),
		e.f.ConstructTuple(memo.ScalarListExpr{trueValue}, types.MakeTuple([]*types.T{types.Bool})),
	}, &memo.ValuesPrivate{Cols: opt.ColList{secondRightCol}, ID: e.f.Metadata().NextUniqueID()})
	nearMiss := e.f.ConstructInnerJoin(left, twoRows, e.filters(predicate), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
