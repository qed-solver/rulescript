package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneMutationFetchCols(t *testing.T) {
	rule := loadRule(t, "PruneMutationFetchCols")

	e := newEnv(t)
	input, cols := e.scan("t", 2)
	mutationCol := e.f.Metadata().AddColumn("mutation", e.v(cols[0]).DataType())
	before := e.f.ConstructProject(
		input,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), mutationCol)},
		opt.ColSet{},
	)
	pruned := e.f.ConstructProject(input, memo.ProjectionsExpr{}, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(
		pruned,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), mutationCol)},
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	oneColumnInput, oneColumnCols := e.scan("u", 1)
	nearMissCol := e.f.Metadata().AddColumn("mutation", e.v(oneColumnCols[0]).DataType())
	nearMiss := e.f.ConstructProject(
		oneColumnInput,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(oneColumnCols[0]), nearMissCol)},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, nearMiss)
}
