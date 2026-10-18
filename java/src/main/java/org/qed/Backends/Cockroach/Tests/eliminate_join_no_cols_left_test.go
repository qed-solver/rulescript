package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateJoinNoColsLeft(t *testing.T) {
	rule := loadRule(t, "EliminateJoinNoColsLeft")
	e := newEnv(t)
	one := e.f.Metadata().AddColumn("one", types.Bool)
	oneRow := func() memo.RelExpr {
		row := e.f.ConstructTuple(memo.ScalarListExpr{e.f.ConstructTrue()}, types.MakeTuple([]*types.T{types.Bool}))
		return e.f.ConstructValues(memo.ScalarListExpr{row}, &memo.ValuesPrivate{
			Cols: opt.ColList{one}, ID: e.f.Metadata().NextUniqueID(),
		})
	}
	emptyLeft := e.f.ConstructProject(oneRow(), memo.ProjectionsExpr{}, opt.ColSet{})
	r, rc := e.scan("t", 3)
	on := e.filters(e.f.ConstructEq(e.v(rc[0]), e.v(rc[1])))
	before := e.f.ConstructInnerJoin(emptyLeft, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructSelect(r, on))

	keptLeft := e.f.ConstructProject(oneRow(), memo.ProjectionsExpr{}, opt.MakeColSet(one))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(keptLeft, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(emptyLeft, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(oneRow(), r, on, &memo.JoinPrivate{}))
}
