package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneOrdinalityCols(t *testing.T) {
	rule := loadRule(t, "PruneOrdinalityCols")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	ordinality := e.f.Metadata().AddColumn("ordinality", types.Int)
	outer := e.f.Metadata().AddColumn("outer", types.Int)
	before := e.f.ConstructProject(e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(cols[0]), e.v(cols[1])}, &memo.FunctionPrivate{Name: "O", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), ordinality),
	}, opt.MakeColSet(cols...)), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(cols[0]), e.v(cols[1]), e.v(ordinality)}, &memo.FunctionPrivate{Name: "G", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), outer),
	}, opt.ColSet{})

	wantInput := input
	wantInput = e.f.ConstructProject(wantInput, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0], cols[1]))
	wantCols := cols
	wantOrdinality := e.f.Metadata().AddColumn("ordinality", types.Int)
	wantOuter := e.f.Metadata().AddColumn("outer", types.Int)
	want := e.f.ConstructProject(e.f.ConstructProject(wantInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(wantCols[0]), e.v(wantCols[1])}, &memo.FunctionPrivate{Name: "O", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), wantOrdinality),
	}, opt.MakeColSet(wantCols[0], wantCols[1])), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(wantCols[0]), e.v(wantCols[1]), e.v(wantOrdinality)}, &memo.FunctionPrivate{Name: "G", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), wantOuter),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearInput, nearCols := e.scan("t", 3)
	nearOrdinality := e.f.Metadata().AddColumn("ordinality", types.Int)
	nearOuter := e.f.Metadata().AddColumn("outer", types.Int)
	near := e.f.ConstructProject(e.f.ConstructProject(nearInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(nearCols[0]), e.v(nearCols[2])}, &memo.FunctionPrivate{Name: "O", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), nearOrdinality),
	}, opt.MakeColSet(nearCols...)), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFunction(memo.ScalarListExpr{e.v(nearCols[0]), e.v(nearCols[1]), e.v(nearOrdinality)}, &memo.FunctionPrivate{Name: "G", Typ: types.Int, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{CalledOnNullInput: true}}), nearOuter),
	}, opt.ColSet{})
	e.expectNoMatch(rule, near)
}
