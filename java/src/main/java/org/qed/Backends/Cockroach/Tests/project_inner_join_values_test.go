package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectInnerJoinValues(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectInnerJoinValues")
	left, leftCols := e.scan("t", 1)
	valueCols := opt.ColList{
		e.f.Metadata().AddColumn("v0", types.Int),
		e.f.Metadata().AddColumn("v1", types.Int),
	}
	values := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(memo.ScalarListExpr{e.num(1), e.num(2)}, types.MakeTuple([]*types.T{types.Int, types.Int})),
	}, &memo.ValuesPrivate{Cols: valueCols, ID: e.f.Metadata().NextUniqueID()})
	on := e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(valueCols[0])))
	before := e.f.ConstructInnerJoin(left, values, on, &memo.JoinPrivate{})

	project := e.f.ConstructProject(left, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(1), valueCols[0]),
		e.f.ConstructProjectionsItem(e.num(2), valueCols[1]),
	}, opt.MakeColSet(leftCols...))
	want := e.f.ConstructSelect(project, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(valueCols[0]))))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructValues(memo.ScalarListExpr{
		e.f.ConstructTuple(memo.ScalarListExpr{e.num(1), e.num(9)}, types.MakeTuple([]*types.T{types.Int, types.Int})),
	}, &memo.ValuesPrivate{Cols: valueCols, ID: e.f.Metadata().NextUniqueID()})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, nearMiss, on, &memo.JoinPrivate{}))
}
