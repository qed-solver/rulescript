package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func nullableColumnScan(e *env, table string, ordinal int) (memo.RelExpr, opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	col := tabID.ColumnID(ordinal)
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(col)}), col
}

func TestEliminateOuterJoin(t *testing.T) {
	rule := loadRule(t, "EliminateOuterJoin")
	e := newEnv(t)
	l, lb := nullableColumnScan(e, "t", 1)
	r, ry := nullableColumnScan(e, "u", 1)
	on := e.filters(e.f.ConstructEq(e.v(lb), e.v(ry)))
	notNull := func(c opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructIsNot(e.v(c), e.f.ConstructNull(types.Int))
	}
	q := e.f.ConstructEq(e.v(lb), e.num(1))
	conds := e.filters(q, notNull(lb), notNull(ry))

	before := e.f.ConstructSelect(e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{}), conds)
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}), conds)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), conds))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}), conds))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{}),
		e.filters(q, notNull(lb))))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructFullJoin(l, r, on, &memo.JoinPrivate{}),
		e.filters(q, notNull(lb), e.f.ConstructIs(e.v(ry), e.f.ConstructNull(types.Int)))))
}
