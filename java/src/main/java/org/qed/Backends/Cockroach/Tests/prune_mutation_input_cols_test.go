package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneMutationInputCols(t *testing.T) {
	rule := loadRule(t, "PruneMutationInputCols")
	t.Run("match", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 2)
		mutationCol := e.f.Metadata().AddColumn("mutation", e.f.Metadata().ColumnMeta(cols[0]).Type)
		before := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructVariable(cols[0]), mutationCol)}, opt.ColSet{})
		pruned := e.f.ConstructProject(input, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0]))
		want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructVariable(cols[0]), mutationCol)}, opt.ColSet{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("second mutation argument", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 2)
		mutationCol := e.f.Metadata().AddColumn("mutation", e.f.Metadata().ColumnMeta(cols[0]).Type)
		before := e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructVariable(cols[1]), mutationCol)}, opt.ColSet{})
		e.expectNoMatch(rule, before)
	})
}
