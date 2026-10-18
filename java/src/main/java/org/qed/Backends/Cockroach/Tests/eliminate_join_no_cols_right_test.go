package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func oneRowNoColsRelation(e *env) memo.RelExpr {
	col := e.f.Metadata().AddColumn("_r", types.Bool)
	row := e.f.ConstructTuple(memo.ScalarListExpr{e.f.ConstructTrue()}, types.MakeTuple([]*types.T{types.Bool}))
	values := e.f.ConstructValues(memo.ScalarListExpr{row}, &memo.ValuesPrivate{
		Cols: opt.ColList{col}, ID: e.f.Metadata().NextUniqueID(),
	})
	return e.f.ConstructProject(values, memo.ProjectionsExpr{}, opt.ColSet{})
}

func TestEliminateJoinNoColsRight(t *testing.T) {
	rule := loadRule(t, "EliminateJoinNoColsRight")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	right := oneRowNoColsRelation(e)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.num(1)))
	before := e.f.ConstructInnerJoin(l, right, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructSelect(l, on))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, right, on, &memo.JoinPrivate{}))
	_, rc := e.scan("t", 1)
	oneRowWithCol := e.intValues([]opt.ColumnID{rc[0]}, []int{1})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, oneRowWithCol, on, &memo.JoinPrivate{}))
}
