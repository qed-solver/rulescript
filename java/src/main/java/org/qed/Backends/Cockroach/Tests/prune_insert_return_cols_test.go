package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneInsertReturnCols(t *testing.T) {
	rule := loadRule(t, "PruneInsertReturnCols")
	e := newEnv(t)
	input, colsCol := singleColumnScan(e, "t", 0)
	cols := []opt.ColumnID{colsCol}
	keyCol := cols[0]
	keptCol := e.f.Metadata().AddColumn("kept", types.Int)
	droppedCol := e.f.Metadata().AddColumn("dropped", types.Int)
	keptExpr := e.f.ConstructFunction(memo.ScalarListExpr{e.v(keyCol)}, &memo.FunctionPrivate{Name: "kept", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}})
	droppedExpr := e.f.ConstructFunction(memo.ScalarListExpr{e.v(keyCol)}, &memo.FunctionPrivate{Name: "dropped", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}})
	inner := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(keptExpr, keptCol),
		e.f.ConstructProjectionsItem(droppedExpr, droppedCol),
	}, opt.MakeColSet(keyCol))
	topCol := e.f.Metadata().AddColumn("top", types.Int)
	topExpr := e.f.ConstructFunction(memo.ScalarListExpr{e.v(keyCol), e.v(keptCol)}, &memo.FunctionPrivate{Name: "top", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}})
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(topExpr, topCol),
	}, opt.ColSet{})

	wantInput, wantKeyCol := input, keyCol
	wantKeptCol := e.f.Metadata().AddColumn("kept", types.Int)
	wantInner := e.f.ConstructProject(wantInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(wantKeyCol)}, &memo.FunctionPrivate{Name: "kept", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), wantKeptCol),
	}, opt.MakeColSet(wantKeyCol))
	wantTopCol := e.f.Metadata().AddColumn("top", types.Int)
	want := e.f.ConstructProject(wantInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(wantKeyCol), e.v(wantKeptCol)}, &memo.FunctionPrivate{Name: "top", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), wantTopCol),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearInput, nearColsCol := singleColumnScan(e, "t", 0)
	nearCols := []opt.ColumnID{nearColsCol}
	nearKeyCol := nearCols[0]
	nearKeptCol := e.f.Metadata().AddColumn("kept", types.Int)
	nearDroppedCol := e.f.Metadata().AddColumn("dropped", types.Int)
	nearInner := e.f.ConstructProject(nearInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(nearKeyCol)}, &memo.FunctionPrivate{Name: "kept", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), nearKeptCol),
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(nearKeyCol)}, &memo.FunctionPrivate{Name: "dropped", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), nearDroppedCol),
	}, opt.MakeColSet(nearKeyCol))
	nearTopCol := e.f.Metadata().AddColumn("top", types.Int)
	nearTopExpr := e.f.ConstructFunction(memo.ScalarListExpr{e.v(nearKeyCol), e.v(nearKeptCol), e.v(nearDroppedCol)}, &memo.FunctionPrivate{Name: "top", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}})
	near := e.f.ConstructProject(nearInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(nearTopExpr, nearTopCol),
	}, opt.ColSet{})
	e.expectNoMatch(rule, near)
}
