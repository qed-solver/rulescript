package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRowLevelOperationRuntimeGroupFiltering(t *testing.T) {
	rule := loadRule(t, "RowLevelOperationRuntimeGroupFiltering")

	t.Run("matching plan", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("nn3", 2)
		cond := e.f.ConstructEq(e.v(lc[1]), e.num(8))
		before := e.f.ConstructSelect(left, e.filters(cond))
		key := e.f.Metadata().AddColumn("group_key", types.Int)
		data := e.f.Metadata().AddColumn("group_data", types.Int)
		group := e.f.ConstructProject(e.f.ConstructSelect(left, e.filters(cond)), memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(lc[0]), key),
			e.f.ConstructProjectionsItem(e.v(lc[1]), data),
		}, opt.ColSet{})
		joined := e.f.ConstructSemiJoin(left, group, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(key))), &memo.JoinPrivate{})
		want := e.f.ConstructSelect(joined, e.filters(e.f.ConstructEq(e.v(lc[1]), e.num(8))))
		e.expectRewriteUpToColumnNames(rule, before, want)
	})

	t.Run("near miss nullable scan", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 2)
		before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[1]), e.num(8))))
		e.expectNoMatch(rule, before)
	})
}
