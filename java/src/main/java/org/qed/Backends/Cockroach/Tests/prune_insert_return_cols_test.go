package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneInsertReturnCols(t *testing.T) {
	rule := loadRule(t, "PruneInsertReturnCols")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	kept := e.f.Metadata().AddColumn("kept", types.Int)
	dropped := e.f.Metadata().AddColumn("dropped", types.Int)
	z := e.f.Metadata().AddColumn("z", types.Int)
	keptItem := func() memo.ProjectionsItem {
		return e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), kept)
	}
	droppedItem := func() memo.ProjectionsItem {
		return e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), dropped)
	}
	top := func(input memo.RelExpr, second opt.ColumnID) memo.RelExpr {
		sum := e.f.ConstructPlus(e.v(cols[0]), e.v(second))
		return e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(sum, z)}, opt.ColSet{})
	}
	insert := e.f.ConstructProject(s, memo.ProjectionsExpr{keptItem(), droppedItem()}, opt.MakeColSet(cols[0]))
	prunedInsert := e.f.ConstructProject(s, memo.ProjectionsExpr{keptItem()}, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, top(insert, kept), top(prunedInsert, kept))
	e.expectNoMatch(rule, top(prunedInsert, kept))
	e.expectNoMatch(rule, top(insert, dropped))
}
