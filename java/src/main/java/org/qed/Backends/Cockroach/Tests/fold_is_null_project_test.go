package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldIsNullProject(t *testing.T) {
	rule := loadRule(t, "FoldIsNullProject")
	e := newEnv(t)
	in, cols := e.scan("t", 2)
	a, b := cols[0], cols[1]
	isNullCol := e.f.Metadata().AddColumn("isnull", types.Bool)
	before := e.f.ConstructProject(in, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIs(e.v(a), memo.NullSingleton), isNullCol),
	}, opt.MakeColSet(b))
	falseCol := e.f.Metadata().AddColumn("isnull", types.Bool)
	after := e.f.ConstructProject(in, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), falseCol),
	}, opt.MakeColSet(b))
	e.expectRewriteUpToFreshColumns(rule, before, after)

	notNullCol := e.f.Metadata().AddColumn("isnotnull", types.Bool)
	e.expectNoMatch(rule, e.f.ConstructProject(in, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIsNot(e.v(a), memo.NullSingleton), notNullCol),
	}, opt.MakeColSet(b)))

	wideIn, wideCols := e.scan("t", 3)
	wideNullCol := e.f.Metadata().AddColumn("isnull", types.Bool)
	e.expectNoMatch(rule, e.f.ConstructProject(wideIn, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIs(e.v(wideCols[0]), memo.NullSingleton), wideNullCol),
	}, opt.MakeColSet(wideCols[1])))

	nullableCol := e.f.Metadata().AddColumn("isnull", types.Bool)
	e.expectNoMatch(rule, e.f.ConstructProject(in, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructIs(e.v(b), memo.NullSingleton), nullableCol),
	}, opt.MakeColSet(a)))
}
