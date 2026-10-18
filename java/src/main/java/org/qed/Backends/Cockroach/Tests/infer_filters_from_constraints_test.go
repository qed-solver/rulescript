package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
)

func inferScan(e *env, table string, idxs ...int) (memo.RelExpr, []opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	cols := make([]opt.ColumnID, len(idxs))
	for i, idx := range idxs {
		cols[i] = tabID.ColumnID(idx)
	}
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(cols...)}), cols
}

func inferIsNotNull(e *env, c opt.ColumnID) opt.ScalarExpr {
	return e.f.ConstructIsNot(e.v(c), memo.NullSingleton)
}

func TestInferFiltersFromConstraints(t *testing.T) {
	rule := loadRule(t, "InferFiltersFromConstraints")
	e := newEnv(t)
	l, lc := inferScan(e, "t", 1)
	r, rc := inferScan(e, "t", 1)
	eq := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])) }
	gt := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(lc[0]), e.num(5)) }
	before := e.f.ConstructInnerJoin(l, r, e.filters(eq(), gt()), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(l, e.filters(gt(), inferIsNotNull(e, lc[0]))),
		e.f.ConstructSelect(r, e.filters(inferIsNotNull(e, rc[0]))),
		e.filters(eq(), gt()),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	l2, lc2 := inferScan(e, "t", 1)
	r2, rc2 := inferScan(e, "t", 1)
	eq2 := func() opt.ScalarExpr { return e.f.ConstructEq(e.v(lc2[0]), e.v(rc2[0])) }
	gt2 := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(lc2[0]), e.num(5)) }
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l2, r2, e.filters(eq2(), gt2()), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l2, r2, e.filters(e.f.ConstructLt(e.v(lc2[0]), e.v(rc2[0])), gt2()), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l2, r2, e.filters(eq2()), &memo.JoinPrivate{}))
	wide, wc := inferScan(e, "t", 0, 1)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(wide, r2, e.filters(e.f.ConstructEq(e.v(wc[1]), e.v(rc2[0])), e.f.ConstructGt(e.v(wc[1]), e.num(5))), &memo.JoinPrivate{}))
}
